package com.issuemoa.user.users.service.users;

import com.issuemoa.user.users.common.CookieUtil;
import com.issuemoa.user.users.common.LoginComponent;
import com.issuemoa.user.users.domain.users.QUsers;
import com.issuemoa.user.users.domain.users.Users;
import com.issuemoa.user.users.domain.users.UsersRepository;
import com.issuemoa.user.users.jwt.TokenProvider;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpHeaders;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor
@Service
public class UsersService implements UserDetailsService {
    private final UsersRepository usersRepository;
    private final JPAQueryFactory jpaQueryFactory;
    private final BCryptPasswordEncoder bCryptPasswordEncoder;
    private QUsers users = QUsers.users;
    private final RedisTemplate<String, Object> redisTemplate;
    private final TokenProvider tokenProvider;
    private final LoginComponent loginComponent;

    public Long save(Users.Request request) {
        request.setPassword(bCryptPasswordEncoder.encode(request.getPassword()));
        return usersRepository.save(request.toEntity()).getId();
    }

    public HashMap<String, Object> findAll(Integer page, Integer pageSize) {
        HashMap<String, Object> resultMap = new HashMap<>();

        List<Users.Response> list = jpaQueryFactory.from(users)
                .offset(page)
                .limit(pageSize)
                .orderBy(users.registerTime.desc())
                .fetch()
                .stream()
                .map(Users.Response::new)
                .collect(Collectors.toList());

        Long totalCnt = (long) jpaQueryFactory.select(users.count()).from(users).fetchOne();

        int totalPage = (int) Math.ceil((float) totalCnt / pageSize);
        totalPage = totalPage == 0 ? 1 : totalPage;

        resultMap.put("list", list);
        resultMap.put("page", page);
        resultMap.put("pageSize", pageSize);
        resultMap.put("totalCnt", totalCnt);
        resultMap.put("totalPage", totalPage);

        return resultMap;
    }

    public Users.Response findById(Long id) {
        return new Users.Response(usersRepository.findById(id).get());
    }

    public int countByEmailAndType(String email, String type) {
        return usersRepository.countByEmailAndType(email, type);
    }

    public boolean findBySocialId(String socialId, HttpServletResponse response) throws IOException {
        Optional<Users> users = usersRepository.findBySocialId(socialId);
        if (users.isPresent()) {
            loginComponent.onSuccess(users.get(), response);
            return true;
        }
        return false;
    }

    @Transactional
    public long updatePassword(Users.Request request) {
        return jpaQueryFactory.update(users)
                .set(users.password, bCryptPasswordEncoder.encode(request.getPassword()))
                .set(users.modifyTime, LocalDateTime.now())
                .where(users.id.eq(request.getId()))
                .execute();
    }

    @Transactional
    public long updateUsersInfo(Users.Request request) {
        return jpaQueryFactory.update(users)
                .set(users.addr, request.getAddr())
                .set(users.addr, request.getAddrPostNo())
                .set(users.modifyTime, LocalDateTime.now())
                .where(users.id.eq(request.getId()))
                .execute();
    }

    @Transactional
    public long updateTempYn(Users.Request request) {
        return jpaQueryFactory.update(users)
                .set(users.dropYn, request.getTempYn())
                .set(users.modifyTime, LocalDateTime.now())
                .where(users.id.eq(request.getId()))
                .execute();
    }

    @Transactional
    public long updateDropYn(Users.Request request) {
        return jpaQueryFactory.update(users)
                .set(users.dropYn, request.getDropYn())
                .set(users.modifyTime, LocalDateTime.now())
                .where(users.id.eq(request.getId()))
                .execute();
    }

    @Transactional
    public long updateName(Users.Request request) {
        return jpaQueryFactory.update(users)
                .set(users.lastName, request.getLastName())
                .set(users.firstName, request.getFirstName())
                .set(users.modifyTime, LocalDateTime.now())
                .where(users.id.eq(request.getId()))
                .execute();
    }

    @Transactional
    public long updateAddress(Users.Request request) {
        return jpaQueryFactory.update(users)
                .set(users.addr, request.getAddr())
                .set(users.addrPostNo, request.getAddrPostNo())
                .set(users.modifyTime, LocalDateTime.now())
                .where(users.id.eq(request.getId()))
                .execute();
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        return usersRepository.findByEmail(email).orElseThrow(() -> new UsernameNotFoundException("==> UsernameNotFoundException"));
    }

    /**
     Access Token + Refresh Token을  검증하고 Redis의 refreshToken 값이 만료전이면
    해당 인증 정보를 가지고 새로운 토큰을 생성한다. */
    public HashMap<String, Object> reissue(HttpServletRequest request, HttpServletResponse response) {

        HashMap<String, Object> resultMap = new HashMap<>();

        String bearerToken = request.getHeader(HttpHeaders.AUTHORIZATION);

        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer")) {
            bearerToken = bearerToken.substring(7);
        } else {
            throw new RuntimeException("==> [Reissue] Empty Access Token.");
        }

        Cookie[] cookies = request.getCookies();

        String refreshToken = CookieUtil.getRefreshTokenCookie(cookies);

        Authentication authentication = tokenProvider.getAuthentication(bearerToken);

        ValueOperations<String, Object> vop = redisTemplate.opsForValue();
        String redisRefreshTokenEmail = (String) vop.get(refreshToken);

        if (!StringUtils.hasText(redisRefreshTokenEmail)) {
            throw new RuntimeException("==> [Reissue Expires] logged out user. ");
        }

        if (!redisRefreshTokenEmail.equals(authentication.getName())) {
            throw new RuntimeException("==> [Reissue] The information in the token does not match.");
        }

        Users details = (Users) authentication.getPrincipal();
        Users users = Users.builder()
                .email(authentication.getName())
                .id(details.getId())
                .build();

        HashMap<String, Object> tokenMap = tokenProvider.generateToken(users);

        resultMap.put("accessToken", tokenMap.get("accessToken"));
        resultMap.put("accessTokenExpires", tokenMap.get("accessTokenExpires"));

        String newRefreshToken = (String) tokenMap.get("refreshToken");
        long newRefreshTokenExpires = Long.parseLong((String) tokenMap.get("refreshTokenExpires"));

        // Redis Set Key-Value
        vop.set(newRefreshToken, authentication.getName(), Duration.ofSeconds(newRefreshTokenExpires));
        // 기존 refershToken은 3초 후 만료
        vop.set(refreshToken, "", Duration.ofSeconds(3));

        // Add Cookie Refersh Token
        response.addCookie(CookieUtil.setRefreshTokenCookie((String) newRefreshToken, newRefreshTokenExpires));

        return resultMap;
    }
}
